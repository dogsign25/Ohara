package com.ohara.controller;

import com.ohara.model.Dto.*;
import com.ohara.service.GraphService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
@Validated
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    // GET /api/graph?limit=100&minStrength=1
    @GetMapping("/graph")
    public GraphResponse getGraph(
        @RequestParam(defaultValue = "100") @Min(10) @Max(500) int limit,
        @RequestParam(defaultValue = "1")   @Min(1)            int minStrength
    ) {
        return graphService.getGraph(limit, minStrength);
    }

    // GET /api/node/{name}
    @GetMapping("/node/{name}")
    public ResponseEntity<NodeDetailDto> getNode(@PathVariable String name) {
        return graphService.getNodeDetail(name)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/node/{name}/articles
    @GetMapping("/node/{name}/articles")
    public ResponseEntity<List<ArticleDto>> getArticles(@PathVariable String name) {
        return graphService.getNodeDetail(name)
            .map(d -> ResponseEntity.ok(d.recentArticles()))
            .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/search?q=NATO
    @GetMapping("/search")
    public List<NodeDto> search(
        @RequestParam String q,
        @RequestParam(defaultValue = "10") @Max(50) int limit
    ) {
        return graphService.search(q, limit);
    }
}
