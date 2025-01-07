import java.util.ArrayList;

public class SampleTest {
    public void test() {
        ArrayList<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        System.out.println(list.get(0));
        System.out.println(list.get(1));
    }

    @org.junit.Test
    public void testWrapper() {
        String msg = "success";
        $testStarted("SampleTest", "testWrapper");
        try {
            test();
        } catch (Exception t) {
            msg = "error: " + t.getMessage();
        } finally {
            $testFinished("SampleTest", "testWrapper");
            $exitProgram("");
        }
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
