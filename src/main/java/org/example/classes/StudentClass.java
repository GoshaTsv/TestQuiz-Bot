package org.example.classes;

import java.util.ArrayList;

public class StudentClass {
    private String name;
    private ArrayList<Long> students;
    private long teacherId;
    private long totalAnswersCount;
    private long correctAnswersCount;
    private long classExperience;

    public long getTotalAnswersCount() {
        return totalAnswersCount;
    }

    public void setTotalAnswersCount(long totalAnswersCount) {
        this.totalAnswersCount = totalAnswersCount;
    }

    public long getCorrectAnswersCount() {
        return correctAnswersCount;
    }

    public void setCorrectAnswersCount(long correctAnswersCount) {
        this.correctAnswersCount = correctAnswersCount;
    }

    public long getClassExperience() {
        return classExperience;
    }

    public void setClassExperience(long classExperience) {
        this.classExperience = classExperience;
    }

    public StudentClass(long teacherId, String name, ArrayList<Long> students) {
        this.name = name;
        this.students = students;
        this.teacherId = teacherId;
    }

    public StudentClass(long teacherId, String name, ArrayList<Long> students, long totalAnswersCount, long correctAnswersCount, long classExperience) {
        this.name = name;
        this.students = students;
        this.teacherId = teacherId;
        this.totalAnswersCount = totalAnswersCount;
        this.correctAnswersCount = correctAnswersCount;
        this.classExperience = classExperience;
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
    public long findLevel(){
        long level = 0;
        while((level +1) * (level + 2) / 2 <= classExperience){
            level++;
        }
        return level;
    }
}
