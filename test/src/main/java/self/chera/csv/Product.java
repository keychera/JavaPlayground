package self.chera.csv;

import self.chera.annotations.AutoCSV;

@AutoCSV
public class Product {
    static String[] header = {"Id", "Name", "Price"};
    public int data;
}
