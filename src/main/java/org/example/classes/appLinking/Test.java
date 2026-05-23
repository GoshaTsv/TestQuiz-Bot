package org.example.classes.appLinking;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Arrays;

public class Test {
    @SerializedName("quizName") //added serialized name to match the generated json's field
    public String testName;
    public ArrayList<Question> questions;

    public String toString() {
        return "Quiz{" +
                "testName='" + testName + '\'' +
                ", questions=" + Arrays.deepToString(questions.toArray()) +
                '}';
    }

    public Test(String testName, ArrayList<Question> questions) {
        this.testName = testName;
        this.questions = questions;
    }

    public void setTestName(String testName) { this.testName = testName; }
    public String getTestName() { return testName; }
}
