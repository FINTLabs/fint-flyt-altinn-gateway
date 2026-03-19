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
public class DrosjeloyveInstanceMapper extends AbstractInstanceMapper {

    public DrosjeloyveInstanceMapper(AltinnFileService altinnFileService) {
        super(altinnFileService);
    }

    @Override
    public String appId() {
        return "vigo/drosjeloyve";
    }

    @Override
    public Mono<InstanceObject> map(Long sourceApplicationId,
                                    KafkaAltinnInstance incomingInstance,
                                    Function<File, Mono<UUID>> persistFile) {

        Mono<List<DocumentEntry>> mandatoryDocuments = mapAltinnDocuments(List.of(
                SOKNAD,
                POLITIATTTEST_DAGLIGLEDER_REF,
                POLITIATTEST_FORETAK_REF
        ), incomingInstance, sourceApplicationId, persistFile);

        Mono<List<DocumentEntry>> ebevisDocuments = mapEbevisDocuments(List.of(
                EBEVIS_KONKURSATTEST_FORETAK_REF,
                EBEVIS_SKATTEATTEST_FORETAK_REF
        ), incomingInstance, sourceApplicationId, persistFile);

        List<String> collectionDocumentRefs = List.of(
                SKATTEATTEST_DAGLIGLEDER_REF,
                KONKURSATTEST_DAGLIGLEDER_REF,
                DOM_FORELEGG_REF,
                DOKUMENTASJON_FAGKOMPETANSE_REF
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

        Stream<Map.Entry<String, String>> foretak = Stream.of(
                entry("foretakOrganisasjonsnummer", incomingInstance.getOrganizationNumber()),
                entry("foretakOrganisasjonsnavn", incomingInstance.getOrganizationName()),
                entry("foretakEpostadresse", incomingInstance.getCompanyEmail()),
                entry("foretakTelefonnummer", emptyIfNull(incomingInstance.getCompanyPhone())),
                entry("foretakFylke", incomingInstance.getCountyName()),
                entry("foretakKommune", emptyIfNull(incomingInstance.getMunicipalityName())),
                entry("foretakGateadresse", incomingInstance.getCompanyAdressStreet()),
                entry("foretakPostnummer", incomingInstance.getCompanyAdressPostcode()),
                entry("foretakPoststed", incomingInstance.getCompanyAdressPostplace())
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

        Stream<Map.Entry<String, String>> transportLeder = Stream.of(
                entry("transportLederFødselsnummer", emptyIfNull(incomingInstance.getTransportmanagerSocialSecurityNumber())),
                entry("transportLederFornavn", emptyIfNull(incomingInstance.getTransportmanagerFirstName())),
                entry("transportLederEtternavn", emptyIfNull(incomingInstance.getTransportmanagerLastName())),
                entry("transportLederEpostadresse", emptyIfNull(incomingInstance.getTransportmanagerEmail())),
                entry("transportLederTelefonnummer", emptyIfNull(incomingInstance.getTransportmanagerPhone())),
                entry("transportLederTilknytning", emptyIfNull(incomingInstance.getTransportmanagerAffiliation()))
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

        return Stream.concat(Stream.concat(Stream.concat(foretak, postadresse), dagligLeder), dokumenter)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
