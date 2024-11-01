package no.fintlabs.instance.gateway.example.collectionandfiles;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;


@Getter
@Jacksonized
@EqualsAndHashCode
@Builder
public class IncomingInstanceWithCollectionOfFiles {
    @NotNull
    private final Integer integerValue1;
    @NotNull
    private final String stringValue1;
    private final String stringValue2;
    private final String stringValue3;
    @NotNull
    private final List<@Valid @NotNull IncomingInstanceCollectionElement> elementCollection1;
}
