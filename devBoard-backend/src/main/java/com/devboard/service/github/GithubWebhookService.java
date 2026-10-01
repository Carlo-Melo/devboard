package com.devboard.service.github;

import com.devboard.event.GithubDeliveryEvent;
import com.devboard.exception.UnauthorizedException;
import com.devboard.repository.BoardRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

@Service @RequiredArgsConstructor @Slf4j
public class GithubWebhookService {
    private final BoardRepository boards;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;
    @Value("${github.webhook-secret:}") private String secret;

    @Transactional
    public void receive(byte[] body, String signature, String deliveryId, String eventType) {
        validate(body, signature);
        if (deliveryId == null || deliveryId.isBlank() || deliveryId.length() > 255 || eventType == null
                || !Set.of("push", "pull_request", "issues").contains(eventType)) return;
        long repoId;
        try { repoId = json.readTree(body).path("repository").path("id").asLong(); }
        catch (Exception e) { log.warn("Webhook GitHub descartado: JSON inválido"); return; }
        var board = boards.findByGithubRepoId(repoId).orElse(null);
        if (board == null || Boolean.TRUE.equals(board.getProject().getArchived())) {
            log.info("Webhook GitHub descartado: repositoryId={}", repoId); return;
        }
        int inserted = jdbc.update("INSERT INTO github_deliveries(id, board_id, repository_id, event_type, payload) VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                deliveryId, board.getId(), repoId, eventType, new String(body, StandardCharsets.UTF_8));
        log.info("Webhook GitHub recebido: boardId={}, evento={}, novo={}", board.getId(), eventType, inserted == 1);
        if (inserted == 1) events.publishEvent(new GithubDeliveryEvent(deliveryId));
    }
    private void validate(byte[] body, String signature) {
        try {
            if (secret == null || secret.isBlank() || signature == null || !signature.startsWith("sha256=")) throw new IllegalArgumentException();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            if (!MessageDigest.isEqual(mac.doFinal(body), HexFormat.of().parseHex(signature.substring(7)))) throw new IllegalArgumentException();
        } catch (Exception e) { throw new UnauthorizedException("Assinatura GitHub inválida"); }
    }
}
