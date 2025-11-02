package com.example.candidate_registry.controller;

import com.example.candidate_registry.entity.Candidate;
import com.example.candidate_registry.service.CandidateService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CandidateController {

    private final CandidateService service;

    public CandidateController(CandidateService service) {
        this.service = service;
    }

    @GetMapping("/candidates")
    public String index(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String nationality,
            @RequestParam(required = false) String origin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "external_ref") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            Model model) {

        Sort.Direction d = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable p = PageRequest.of(Math.max(0, page), size, Sort.by(d, sort));
        Page<Candidate> pageData = service.search(name, nationality, origin, p);

        model.addAttribute("candidates", pageData);
        model.addAttribute("name", name);
        model.addAttribute("nationality", nationality);
        model.addAttribute("origin", origin);
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        return "candidates/index";
    }
}
