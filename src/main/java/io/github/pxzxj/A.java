package io.github.pxzxj;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class A {

    public static void main(String[] args) throws IOException {
        String s = "img/7871/71747/01-02p.jpg";
        Pattern imageNamePattern = Pattern.compile("/([^/]+\\.jpg)");
        Matcher matcher = imageNamePattern.matcher(s);
        if(matcher.find()) {
            String group = matcher.group(1);
            System.out.println("group = " + group);
        }
    }
}
