package no.fintlabs.instance.gateway.example.collectionandfiles;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotNull;

@Getter
@Jacksonized
@EqualsAndHashCode
@Builder
public class IncomingInstanceCollectionElement {
    @NotNull
    private final String stringValue;
    @NotNull
    private final String filename;
    @NotNull
    private final String fileContentBase64;
}
