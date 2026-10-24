package com.bsmi.attendancesystem;

public class StudentAttendanceData {
    private final String studentNum;
    private final String semester;
    private final String course;
    private final String entryTime;
    private final String exitTime;
    private final String status;
    public StudentAttendanceData(String studentNum, String semester,String course, String entryTime, String exitTime, String status) {
        this.studentNum = studentNum;
        this.semester = semester;
        this.course = course;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.status = status;
    }
    public String getStudentNum() {
        return studentNum;
    }

    public String getSemester() {
        return semester;
    }

    public String getCourse() {
        return course;
    }

    public String getEntryTime() {
        return entryTime;
    }

    public String getExitTime() {
        return exitTime;
    }

    public String getStatus() {
        return status;
    }
}
