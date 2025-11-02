package com.example.candidate_registry.dto;

import com.example.candidate_registry.entity.Candidate;

public class CandidateDto {
    public String externalRef;
    public String name;
    public Integer age;
    public String nationality;
    public String origin;
    public String notes;

    public CandidateDto() {
    }

    public CandidateDto(Candidate c) {
        this.externalRef = c.getExternalRef();
        this.name = c.getName();
        this.age = c.getAge();
        this.nationality = c.getNationality();
        this.origin = c.getOrigin();
        this.notes = c.getNotes();
    }
}
