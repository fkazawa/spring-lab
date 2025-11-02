package com.example.candidate_registry.repository;

import com.example.candidate_registry.entity.Candidate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    Optional<Candidate> findByExternalRef(String externalRef);

    Page<Candidate> findByNameContainingIgnoreCaseAndNationalityContainingIgnoreCaseAndOriginContainingIgnoreCase(
            String name, String nationality, String origin, Pageable pageable);
}
