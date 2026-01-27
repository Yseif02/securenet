package edu.yu.mdm;

import java.util.*;

public class RandomDataGenerator {

    private static final HashMap<String, Double> productPrices = new HashMap<>();

    private static final List<String> products = List.of(
            "Laptop",
            "Smartphone",
            "Tablet",
            "Headphones",
            "Keyboard",
            "Mouse",
            "Monitor",
            "Webcam",
            "Microphone",
            "Printer",
            "Scanner",
            "Router",
            "Modem",
            "ExternalHardDrive",
            "SSD",
            "USBFlashDrive",
            "PowerBank",
            "SmartWatch",
            "BluetoothSpeaker",
            "DeskLamp",
            "GamingChair",
            "DockingStation",
            "GraphicsCard",
            "CPU",
            "RAM"
    );

    static {
        Random random = new Random();

        for (String product : products) {
            double price = 5 + (45 * random.nextDouble()); // range [5, 50)
            productPrices.put(product, price);
        }
    }

    public static String randomProductName() {
        Random random = new Random();
        return products.get(random.nextInt(products.size()));
    }

    public static double getProductPrice(String product) {
        return productPrices.get(product);
    }


    private static final List<String> firstNames = List.of(
            "Adam","Ben","Chris","Daniel","Eli","Frank","George","Henry","Isaac","Jack",
            "Kevin","Leo","Michael","Nathan","Owen","Paul","Quinn","Ryan","Samuel","Tom",
            "Uri","Victor","William","Xavier","Yosef","Zach","Aaron","Brian","Caleb","David",
            "Ethan","Felix","Gavin","Hunter","Ian","Jacob","Kyle","Lucas","Mark","Noah",
            "Oliver","Peter","Robert","Sean","Tyler","Umar","Vincent","Wyatt","Yoni","Zane"
    );

    private static final List<String> lastNames = List.of(
            "Smith","Johnson","Williams","Brown","Jones","Garcia","Miller","Davis","Rodriguez","Martinez",
            "Hernandez","Lopez","Gonzalez","Wilson","Anderson","Thomas","Taylor","Moore","Jackson","Martin",
            "Lee","Perez","Thompson","White","Harris","Sanchez","Clark","Ramirez","Lewis","Robinson",
            "Walker","Young","Allen","King","Wright","Scott","Torres","Nguyen","Hill","Flores",
            "Green","Adams","Nelson","Baker","Hall","Rivera","Campbell","Mitchell","Carter","Roberts"
    );

    public static String randomName() {
        Random random = new Random();

        String first = firstNames.get(random.nextInt(firstNames.size()));
        String last = lastNames.get(random.nextInt(lastNames.size()));

        return first + last;
    }
}