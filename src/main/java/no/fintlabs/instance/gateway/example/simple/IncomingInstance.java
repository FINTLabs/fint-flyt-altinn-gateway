package no.fintlabs.instance.gateway.example.simple;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotNull;

@Getter
@Jacksonized
@EqualsAndHashCode
@Builder
public class IncomingInstance {
    @NotNull
    private final Integer integerValue1;
    @NotNull
    private final String stringValue1;
    private final String stringValue2;
    private final String stringValue3;
}
