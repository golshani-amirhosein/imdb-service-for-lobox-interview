package com.lobox.interview.controller;

import com.lobox.interview.service.RequestCounterService;
import com.lobox.interview.service.dto.EndpointCallCount;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final RequestCounterService requestCounterService;

    @GetMapping("/requests-count")
    public List<EndpointCallCount> getRequestsCount() {
        return requestCounterService.getAll();
    }
}
