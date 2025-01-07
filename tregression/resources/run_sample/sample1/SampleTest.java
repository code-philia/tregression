import java.util.ArrayList;

import org.junit.Test;

public class SampleTest {
    @Test
    public void test() {
        ArrayList<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        System.out.println(list.get(0));
        System.out.println(list.get(1));
    }
}
