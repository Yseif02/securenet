const fs = require("fs");
const { MongoClient } = require("mongodb");

function buildBaseAccountTransactions(now, depositAmount, reservedAmount) {
    return [
        {
            date: now,
            amount: depositAmount,
            transaction_code: "deposit"
        },
        {
            date: now,
            amount: reservedAmount,
            transaction_code: "buy",
            symbol: "yu",
            price: 613.00
        }
    ];
}

function buildOtherAccountTransactions(now, transferredAmount) {
    return [
        {
            date: now,
            amount: transferredAmount,
            transaction_code: "transfer"
        },
        {
            date: now,
            amount: transferredAmount,
            transaction_code: "buy",
            symbol: "yu",
            price: 613.00
        }
    ];
}

async function main() {
    const now = new Date();
    const args = process.argv;

    if (args.length < 3) {
        console.error("Error: Not enough arguments");
        process.exit(1);
    }

    const inputFile = args[2];
    const fileContents = fs.readFileSync(inputFile, "utf8").trim();

    const parts = fileContents.split(/\s+/);
    if (parts.length !== 3) {
        throw new Error("Input file missing arguments");
    }

    const uri = parts[0];
    const accountId = Number(parts[1]);
    const amount = Number(parts[2]);

    if (Number.isNaN(accountId)) {
        throw new Error("accountId must be a number");
    }

    if (amount < 1) {
        throw new Error("Amount is less than 1");
    }

    const client = new MongoClient(uri);

    try {
        await client.connect();

        const db = client.db("sample_analytics");
        const accountsCollection = db.collection("accounts");
        const customersCollection = db.collection("customers");
        const transactionsCollection = db.collection("transactions");

        const baseAccount = await accountsCollection.findOne({
            account_id: accountId
        });

        if (!baseAccount) {
            throw new Error("Account does not exist");
        }
        console.log("Found Base account");
        //console.log(baseAccount);

        const customer = await customersCollection.findOne({
            accounts: accountId
        });

        if (!customer) {
            throw new Error("Customer for account not found");
        }
        console.log("Found customer");
        //console.log(customer);

        const allAccountIds = customer.accounts;
        const otherAccountIds = allAccountIds.filter((id) => id !== accountId);

        console.log("All customer account IDs:", allAccountIds);
        console.log("Base account ID:", accountId);
        console.log("Other customer account IDs:", otherAccountIds);

        const reservedAmount = amount / 6;
        const distributableAmount = amount - reservedAmount;

        let perOtherAccountAmount = 0;
        if (otherAccountIds.length > 0) {
            perOtherAccountAmount = distributableAmount / otherAccountIds.length;
        }

        console.log("Reserved amount for base account:", reservedAmount);
        console.log("Total amount to distribute:", distributableAmount);
        console.log("Amount per other account:", perOtherAccountAmount);

        const otherAccounts = await  accountsCollection.find({
            account_id: { $in: otherAccountIds}
        }).toArray();

        if (otherAccounts.length !== otherAccountIds.length) {
            throw new Error(("One or more other accounts were not found"));
        }
        console.log("Other account documents:");
        //console.log(otherAccounts);

        //calc
        const baseAccountNewLimit = baseAccount.limit;

        const otherAccountUpdatePlans = otherAccounts.map((otherAccount) => {
            return {
                account_id: otherAccount.account_id,
                old_limit: otherAccount.limit,
                new_limit: otherAccount.limit,
                transaction_count_increment: 2,
                transfer_amount: perOtherAccountAmount,
                buy_amount: perOtherAccountAmount
            };
        });

        const customerTransactionCountIncrement = (1 + otherAccounts.length) * 2;
        const transactionDocsToInsert = 1 + otherAccounts.length;

        console.log("Planned base account update:");
        /*console.log({
            account_id: baseAccount.account_id,
            old_limit: baseAccount.limit,
            new_limit: baseAccountNewLimit,
            transaction_count_increment: 2,
            deposit_amount: amount,
            reserved_amount: reservedAmount
        });*/

        console.log("Planned other account updates:");
        //console.log(otherAccountUpdatePlans);

        console.log("Planned customer update:");
        /*console.log({
            customer_id: customer._id,
            transaction_count_increment: customerTransactionCountIncrement
        });*/

        //console.log("Transaction documents to insert:", transactionDocsToInsert);
        const transactionDocs = []

        transactionDocs.push({
            account_id: baseAccount.account_id,
            bucket_start_date: now,
            bucket_end_date: now,
            transaction_count: 2,
            transactions: buildBaseAccountTransactions(now, amount, reservedAmount)
        });

        for (const otherAccount of otherAccounts) {
            transactionDocs.push({
                account_id: otherAccount.account_id,
                bucket_start_date: now,
                bucket_end_date: now,
                transaction_count: 2,
                transactions: buildOtherAccountTransactions(now, perOtherAccountAmount)
            });
        }

        console.log("Planned transaction documents:");
        //console.log(JSON.stringify(transactionDocs, null, 2));


        const session = client.startSession();

        try {
            await session.withTransaction(async  () => {
                //const nowDate = new Date();

                await accountsCollection.updateOne(
                    { account_id: accountId},
                    {
                        $set: {
                            last_transaction_date: now,
                            limit: baseAccountNewLimit
                        },
                        $inc: {
                            transaction_count: 2
                        }
                    },
                    { session }
                );
                // throw new Error("test rollback");


                for (const plan of otherAccountUpdatePlans) {
                    await accountsCollection.updateOne(
                        { account_id: plan.account_id},
                        {
                            $set: {
                                last_transaction_date: now,
                                limit: plan.new_limit
                            },
                            $inc: {
                                transaction_count: plan.transaction_count_increment
                            }
                        },
                        {session}
                    );
                }

                await customersCollection.updateOne(
                    {_id: customer._id},
                    {
                        $set: {
                            last_transaction_date: now,
                        },
                        $inc: {
                            transaction_count: customerTransactionCountIncrement
                        }
                    },
                    { session }
                );

                await transactionsCollection.insertMany(transactionDocs, { session });
            }, {
                readPreference: "primary",
                readConcern: { level: "snapshot"},
                writeConcern: { w: "majority"}
            });
            console.log("Transaction commited successfully");
        } finally {
            await session.endSession();
        }

    } finally {
        await client.close();
    }
}

main().catch((err) => {
   /* console.error(err);
    process.exit(1);*/
    throw new Error();
});