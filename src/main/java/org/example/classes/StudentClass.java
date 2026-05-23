package org.example.classes;

import java.util.ArrayList;

public class StudentClass {
    private String name;
    private ArrayList<Long> students;
    private long teacherId;

    public StudentClass(long teacherId, String name, ArrayList<Long> students) {
        this.name = name;
        this.students = students;
        this.teacherId = teacherId;
    }

    public String getName() {
        return name;
    }

    public ArrayList<Long> getStudents() {
        return students;
    }

    public long getTeacherId() {
        return teacherId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStudents(ArrayList<Long> students) {
        this.students = students;
    }

    public void setTeacherId(long teacherId) {
        this.teacherId = teacherId;
    }

    @Override
    public String toString() {
        return "StudentClass{" +
                "name='" + name + '\'' +
                ", students=" + students +
                ", teacherId=" + teacherId +
                '}';
    }
}
