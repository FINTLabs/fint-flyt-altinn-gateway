package no.fintlabs.altinn;

import no.fintlabs.gateway.instance.model.File;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;

@Service
public class AltinnFileService {
    private final WebClient webClient;

    public AltinnFileService(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<File> fetchFile(String instanceId, String documentReference, Long sourceApplicationId) {
        return webClient.get()
                .uri(String.format("/api/file/%s/%s", instanceId, documentReference))
                .exchangeToMono(response -> response.bodyToMono(byte[].class)
                        .map(body -> {
                            HttpHeaders httpHeaders = HttpHeaders.writableHttpHeaders(response.headers().asHttpHeaders());
                            return File.builder()
                                    .name(getFilenameFromHeaders(httpHeaders))
                                    .sourceApplicationId(sourceApplicationId)
                                    .sourceApplicationInstanceId(instanceId)
                                    .type(response.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM))
                                    .encoding("base64")
                                    .base64Contents(Base64.getEncoder().encodeToString(body))
                                    .build();
                        }))
                .doOnError(throwable -> {
                    throw new RuntimeException(String.format("Failed to fetch file with reference %s", documentReference),  throwable);
                });
    }

    private String getFilenameFromHeaders(HttpHeaders headers) {
        return headers.getContentDisposition().getFilename();
    }

}
