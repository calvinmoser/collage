package com.techietable.collage.service;

import com.techietable.collage.model.ContactRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Service
public class MatrixService {

    private static final Logger log = LoggerFactory.getLogger(MatrixService.class);

    @Value("${matrix.homeserver-url}")
    private String homeserverUrl;

    @Value("${matrix.access-token}")
    private String accessToken;

    @Value("${matrix.room-id}")
    private String roomId;

    private final RestClient restClient = RestClient.create();

    public void sendContactMessage(ContactRequest req) {
        log.info("Contact request — name=\"{}\" email=\"{}\" message=\"{}\" wantHandle={} handle=\"{}\"",
                req.name(), req.email(), req.message(), req.wantHandle(), req.handle());

        if (accessToken.isBlank() || roomId.isBlank()) {
            throw new IllegalStateException("Matrix credentials not configured");
        }
        String body = buildMessage(req);
        String txnId = UUID.randomUUID().toString().replace("-", "");

        restClient.put()
            .uri(homeserverUrl + "/_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}", roomId, txnId)
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("msgtype", "m.text", "body", body))
            .retrieve()
            .toBodilessEntity();
    }

    private String buildMessage(ContactRequest req) {
        StringBuilder sb = new StringBuilder("New contact request\n");
        sb.append("Name: ").append(req.name()).append("\n");
        sb.append("Email: ").append(req.email()).append("\n");
        if (req.message() != null && !req.message().isBlank()) {
            sb.append("Message: ").append(req.message()).append("\n");
        }
        if (req.wantHandle() && req.handle() != null && !req.handle().isBlank()) {
            sb.append("Handle: @").append(req.handle()).append(":techietable.com");
        } else {
            sb.append("Handle: no request");
        }
        return sb.toString();
    }
}
