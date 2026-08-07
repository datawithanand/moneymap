package com.moneymap.web;

import com.moneymap.model.User;
import com.moneymap.service.SearchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Global keyword search across every entity type the current user owns. Read-only. */
@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/search")
    public List<SearchService.SearchResult> search(@RequestParam String q, HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        return searchService.search(user, q);
    }
}
