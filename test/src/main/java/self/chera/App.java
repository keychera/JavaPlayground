package self.chera;

public class App {
    public static void main(String[] args) {
        var personBuilder = new AnnotationUserBuilder();
        var person = personBuilder.setData1(1).setData2(2).build();
        System.out.println(person.data1);
    }
}
