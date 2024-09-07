package tregression.auto;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;

public class Atest {
	public static void main(String[] args) {
        // 指定.class文件的路径
        File file = new File("D:\\MutationDataset\\MutationFiles\\Chart\\1\\TextBlock.java");
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file.getAbsolutePath()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("package ")) {
                    String packageName = line.substring(8, line.indexOf(';')).trim();
                    System.out.println("包名: " + packageName);
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
	
}
