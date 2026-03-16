package no.fintlabs.flyt;

import lombok.extern.slf4j.Slf4j;
import no.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.fintlabs.altinn.AltinnFileService;
import no.fintlabs.gateway.instance.model.File;
import no.fintlabs.gateway.instance.model.instance.InstanceObject;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class DrosjesentralInstanceMapper extends AbstractInstanceMapper {

    public DrosjesentralInstanceMapper(AltinnFileService altinnFileService) {
        super(altinnFileService);
    }

    @Override
    public String appId() {
        return "vigo/drosjesentral";
    }

    @Override
    public Mono<InstanceObject> map(Long sourceApplicationId,
                                    KafkaAltinnInstance incomingInstance,
                                    Function<File, Mono<UUID>> persistFile) {

        Mono<List<DocumentEntry>> mandatoryDocuments = mapAltinnDocuments(List.of(
                SOKNAD,
                POLITIATTTEST_DAGLIGLEDER_REF
        ), incomingInstance, sourceApplicationId, persistFile);

        Mono<List<DocumentEntry>> ebevisDocuments = mapEbevisDocuments(List.of(
                EBEVIS_KONKURSATTEST_FORETAK_REF,
                EBEVIS_SKATTEATTEST_FORETAK_REF
        ), incomingInstance, sourceApplicationId, persistFile);

        List<String> collectionDocumentRefs = List.of(
                SKATTEATTEST_DAGLIGLEDER_REF,
                KONKURSATTEST_DAGLIGLEDER_REF,
                DOM_FORELEGG_REF,
                BESKRIVELSE_REF
        );

        Mono<List<DocumentEntry>> collectionDocuments = mapAltinnDocuments(collectionDocumentRefs,
                incomingInstance, sourceApplicationId, persistFile);

        return Mono.zip(mandatoryDocuments, ebevisDocuments, collectionDocuments)
                .map(zip -> {
                            List<DocumentEntry> allMandatoryDocuments = Stream.of(zip.getT1(), zip.getT2()).flatMap(List::stream).toList();

                            return InstanceObject.builder()
                                    .valuePerKey(toValuePerKey(incomingInstance, allMandatoryDocuments))
                                    .objectCollectionPerKey(mapCollectionsDynamically(zip.getT3(), collectionDocumentRefs))
                                    .build();
                        }
                );
    }

    @Override
    protected Map<String, String> toValuePerKey(KafkaAltinnInstance incomingInstance, List<DocumentEntry> altinnDocuments) {
        log.info("Mapping incoming instance with {} documents: {}", altinnDocuments.size(), altinnDocuments);

        Stream<Map.Entry<String, String>> virksomhet = Stream.of(
                entry("virksomhetOrganisasjonsnummer", incomingInstance.getOrganizationNumber()),
                entry("virksomhetOrganisasjonsnavn", incomingInstance.getOrganizationName()),
                entry("virksomhetEpostadresse", incomingInstance.getCompanyEmail()),
                entry("virksomhetTelefonnummer", emptyIfNull(incomingInstance.getCompanyPhone())),
                entry("virksomhetFylke", incomingInstance.getCountyName()),
                entry("virksomhetKommune", emptyIfNull(incomingInstance.getMunicipalityName())),
                entry("virksomhetGateadresse", incomingInstance.getCompanyAdressStreet()),
                entry("virksomhetPostnummer", incomingInstance.getCompanyAdressPostcode()),
                entry("virksomhetPoststed", incomingInstance.getCompanyAdressPostplace())
        );

        Stream<Map.Entry<String, String>> postadresse = Stream.of(
                entry("postadresseGateadresse", emptyIfNull(incomingInstance.getPostalAdressStreet())),
                entry("postadressePostnummer", emptyIfNull(incomingInstance.getPostalAdressPostcode())),
                entry("postadressePoststed", emptyIfNull(incomingInstance.getPostalAdressPostplace()))
        );

        Stream<Map.Entry<String, String>> dagligLeder = Stream.of(
                entry("dagligLederFødselsnummer", emptyIfNull(incomingInstance.getManagerSocialSecurityNumber())),
                entry("dagligLederFornavn", emptyIfNull(incomingInstance.getManagerFirstName())),
                entry("dagligLederEtternavn", emptyIfNull(incomingInstance.getManagerLastName())),
                entry("dagligLederEpostadresse", emptyIfNull(incomingInstance.getManagerEmail())),
                entry("dagligLederTelefonnummer", emptyIfNull(incomingInstance.getManagerPhone()))
        );

        Stream<Map.Entry<String, String>> dokumenter = altinnDocuments.stream()
                .flatMap(doc -> {
                    Map<String, String> values = DOCUMENT_MAPPINGS.get(doc.reference());
                    String prefix = values.get("prefix");
                    return Stream.of(
                            entry(prefix + "Tittel", values.get("title")),
                            entry(prefix + "Format", String.valueOf(doc.type())),
                            entry(prefix + "Fil", doc.id())
                    );
                });

        return Stream.concat(Stream.concat(Stream.concat(virksomhet, postadresse), dagligLeder), dokumenter)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

}
