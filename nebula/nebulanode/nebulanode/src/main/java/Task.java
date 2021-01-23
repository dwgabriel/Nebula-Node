import java.io.File;
import java.util.LinkedHashMap;

public class Task {

    File taskFile;
    LinkedHashMap<String,String> taskParamsMap = new LinkedHashMap<>();

    public Task(File taskFile, LinkedHashMap<String, String> taskParamsMap) {
        this.taskFile = taskFile;
        this.taskParamsMap = taskParamsMap;
    }


}
