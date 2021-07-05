public class AnnotationUser {
    public int data1;
    public int data2;
    public int data3;

    @BuilderProperty
    void setData1(int data) {
        data1 = data;
    }

    @BuilderProperty
    void setData2(int data) {
        data2 = data;
    }
}
