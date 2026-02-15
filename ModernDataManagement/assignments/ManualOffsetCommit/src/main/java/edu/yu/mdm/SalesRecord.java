package edu.yu.mdm;

public class SalesRecord {
    private final String productName;
    private final int quantity;
    private final double price;
    private final long timestamp;
    private final long recordId;
    private final String customerName;

    public SalesRecord(String productName, int quantity, double price, long timestamp, long recordId, String customerName) {
        this.productName = productName;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = timestamp;
        this.recordId = recordId;
        this.customerName = customerName;
    }

    public String getProductName() {return productName;}
    public int getQuantity() {return quantity;}
    public double getPrice() {return price;}
    public long getTimestamp() {return timestamp;}
    public long getRecordId() {return recordId;}
    public String getCustomerName() {return customerName;}

    public String toKafkaValue() {
        return String.format("%s|%d|%.2f|%d|%d|%s", productName, quantity, price, timestamp, recordId, customerName);
    }

    public static SalesRecord fromKafkaValue(String value) {
        String[] parts = value.split("\\|");
        return new SalesRecord(
                parts[0], //productName
                Integer.parseInt(parts[1]), //quantity
                Double.parseDouble(parts[2]), //price
                Long.parseLong(parts[3]), //timestamp
                Long.parseLong(parts[4]), //recordId
                parts[5] //customerName
                );
    }

    @Override
    public String toString() {
        return String.format("SalesRecord{product=%s, qty=%d, price=%.2f, timestamp=%d, recordId=%d, customer=%s}",
                productName, quantity, price, timestamp, recordId, customerName);
    }
}
