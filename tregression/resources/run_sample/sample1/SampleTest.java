import java.util.ArrayList;

import org.junit.Test;

public class SampleTest {
    @Test
    public void test() {
        String msg = "success";
        $testStarted("SampleTest", "testWrapper");
        try {
            testInner();
        } catch (Exception t) {
            msg = "error: " + t.getMessage();
        } finally {
            $testFinished("SampleTest", "testWrapper");
            $exitProgram("");
        }
    }

    public void testInner() {
        ArrayList<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        System.out.println(list.get(0));
        System.out.println(list.get(1));
    }

    public void $testFinished(String className, String methodName) {
        // for agent part.
    }

    public void $testStarted(String className, String methodName) {
        // for agent part.
    }

    public void $exitProgram(String resultMsg) {
        // for agent part.
    }
}
