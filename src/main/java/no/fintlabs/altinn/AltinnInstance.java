package no.fintlabs.altinn;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotNull;

@Getter
@Jacksonized
@EqualsAndHashCode
@Builder
public class AltinnInstance {

    @NotNull
    private final String instanceId;
}
