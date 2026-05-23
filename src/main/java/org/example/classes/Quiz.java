package org.example.classes;
import org.example.classes.appLinking.Test;

public class Quiz {
    private long teacherId;
    private StudentClass studentClass;
    private Test test;

    public Quiz(long teacherId, StudentClass studentClass, Test test) {
        this.teacherId = teacherId;
        this.studentClass = studentClass;
        this.test = test;
    }

    public long getTeacherId() {
        return teacherId;
    }

    public StudentClass getStudentClass() {
        return studentClass;
    }

    public Test getTest() {
        return test;
    }

    public void setTeacherId(long teacherId) {
        this.teacherId = teacherId;
    }

    public void setStudentClass(StudentClass studentClass) {
        this.studentClass = studentClass;
    }

    public void setTest(Test test) {
        this.test = test;
    }
}
