package com.hitorro.prbench.controller;

import com.hitorro.prbench.entity.Bot;
import com.hitorro.prbench.repository.BotRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bots")
@CrossOrigin(origins = "*")
public class BotController {

    private final BotRepository botRepo;

    public BotController(BotRepository botRepo) { this.botRepo = botRepo; }

    @GetMapping
    public List<Bot> list() { return botRepo.findAll(); }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        var bot = new Bot();
        bot.setName((String) body.get("name"));
        bot.setDescription((String) body.get("description"));
        bot.setWorkflowFileName((String) body.get("workflowFileName"));
        bot.setWorkflowContent((String) body.get("workflowContent"));
        bot.setWaitStrategy((String) body.getOrDefault("waitStrategy", "BOTH"));
        if (body.containsKey("timeoutSeconds"))
            bot.setTimeoutSeconds(((Number) body.get("timeoutSeconds")).intValue());
        return ResponseEntity.ok(botRepo.save(bot));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return botRepo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return botRepo.findById(id).map(bot -> {
            if (body.containsKey("name")) bot.setName((String) body.get("name"));
            if (body.containsKey("description")) bot.setDescription((String) body.get("description"));
            if (body.containsKey("workflowFileName")) bot.setWorkflowFileName((String) body.get("workflowFileName"));
            if (body.containsKey("workflowContent")) bot.setWorkflowContent((String) body.get("workflowContent"));
            if (body.containsKey("waitStrategy")) bot.setWaitStrategy((String) body.get("waitStrategy"));
            if (body.containsKey("timeoutSeconds"))
                bot.setTimeoutSeconds(((Number) body.get("timeoutSeconds")).intValue());
            return ResponseEntity.ok(botRepo.save(bot));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        botRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
