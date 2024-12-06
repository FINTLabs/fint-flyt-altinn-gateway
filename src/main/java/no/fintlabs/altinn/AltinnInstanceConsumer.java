package no.fintlabs.altinn;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import no.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.fintlabs.gateway.instance.InstanceProcessor;
import no.fintlabs.kafka.entity.EntityConsumerConfiguration;
import no.fintlabs.kafka.entity.EntityConsumerFactoryService;
import no.fintlabs.kafka.entity.topic.EntityTopicNameParameters;
import no.fintlabs.kafka.entity.topic.EntityTopicService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;

@Slf4j
@Component
public class AltinnInstanceConsumer {

    @Value("${fint.flyt.altinn.sso.client-id}")
    private String clientId;

    @Value("${fint.flyt.altinn.sso.client-secret}")
    private String clientSecret;

    @Autowired
    private WebClient webClient;

    private final EntityTopicNameParameters entityTopicNameParameters;
    private final InstanceProcessor<KafkaAltinnInstance> instanceProcessor;

    public AltinnInstanceConsumer(
            EntityTopicService entityTopicService,
            @Value("${fint.org-id}") String orgId,
            InstanceProcessor<KafkaAltinnInstance> instanceProcessor) {
        this.entityTopicNameParameters = EntityTopicNameParameters.builder()
                .orgId(orgId).domainContext("altinn").resource("instance-received")
                .build();
        this.instanceProcessor = instanceProcessor;
    }

    private Authentication createAuthentication() {
        try {
            String token = webClient.post()
                    .uri("https://idp.felleskomponent.no/nidp/oauth/nam/token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue("grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(node -> node.get("access_token").asText())
                    .block();

            return new UsernamePasswordAuthenticationToken("system", token,
                    Collections.singletonList(new SimpleGrantedAuthority("SOURCE_APPLICATION_ID_5")));

        } catch (Exception e) {
            log.error("Failed to create authentication: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to create authentication", e);
        }
    }

    private void process(ConsumerRecord<String, KafkaAltinnInstance> altinnInstanceRecord) {
        try {
            log.info("Congratulations! 🎉 You received a new instance with instanceId {} from organizationName {} in county {}",
                    altinnInstanceRecord.value().getInstanceId(),
                    altinnInstanceRecord.value().getOrganizationName(),
                    altinnInstanceRecord.value().getCountyName());

            Authentication authentication = createAuthentication();
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            instanceProcessor.processInstance(authentication, altinnInstanceRecord.value());

        } catch (Exception e) {
            log.error("Error processing Altinn instance with instanceId {}: {}",
                    altinnInstanceRecord.value().getInstanceId(),
                    e.getMessage(), e);
            throw e;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, KafkaAltinnInstance> altinnInstanceConsumerConfiguration(
            EntityConsumerFactoryService entityConsumerFactoryService) {

        log.info("Creating consumer with domainContext {}, orgId {}, and resource {}",
                entityTopicNameParameters.getDomainContext(),
                entityTopicNameParameters.getOrgId(),
                entityTopicNameParameters.getResource());
        
        return entityConsumerFactoryService
                .createRecordConsumerFactory(
                    KafkaAltinnInstance.class,
                    this::process,
                        EntityConsumerConfiguration.builder()
                                .ackMode(ContainerProperties.AckMode.RECORD)
                                .build()
                )
                .createContainer(entityTopicNameParameters);
    }
}