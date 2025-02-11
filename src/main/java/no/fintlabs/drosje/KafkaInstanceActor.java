package no.fintlabs.drosje;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KafkaInstanceActor {
    private String altinnReference;
    private String organizationNumber;
    private String socialSecurityNumber;
}
