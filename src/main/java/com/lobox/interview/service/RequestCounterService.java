package com.lobox.interview.service;

import com.lobox.interview.service.dto.EndpointCallCount;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RequestCounterService {

    private final ConcurrentHashMap<String, Long> counter = new ConcurrentHashMap<>();
    private final AtomicLong totalCount = new AtomicLong();

    public void increment(String endpoint) {
        counter.compute(endpoint, (key, value) -> value == null ? 1L : value + 1);
        totalCount.incrementAndGet();
    }

    public List<EndpointCallCount> getAll() {
        var result = new ArrayList<EndpointCallCount>();

        var endpointStats = counter.keySet().stream()
                .map(r -> new EndpointCallCount(r, counter.get(r)))
                .toList();

        result.add(new EndpointCallCount("Total", totalCount.get()));
        result.addAll(endpointStats);

        return result;
    }
}
