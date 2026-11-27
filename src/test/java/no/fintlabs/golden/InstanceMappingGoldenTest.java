package no.fintlabs.golden;

import no.fintlabs.altinn.AltinnFileService;
import no.fintlabs.flyt.DrosjeloyveInstanceMapper;
import no.fintlabs.flyt.DrosjesentralInstanceMapper;
import no.fintlabs.flyt.IncomingInstanceMappingService;
import no.novari.flyt.gateway.instance.model.File;
import no.novari.flyt.gateway.instance.model.instance.InstanceObject;
import no.novari.fint.altinn.model.kafka.KafkaAltinnInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static no.fintlabs.golden.GoldenFiles.assertMatchesGolden;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Locks what the gateway sends to FLYT for an Altinn instance: the files it fetches from fint-altinn-service,
 * the files it uploads and the instance object. Uses the real AltinnFileService on a stubbed HTTP exchange.
 */
class InstanceMappingGoldenTest {

    private static final long SOURCE_APPLICATION_ID = 5L;
    private static final String INSTANCE_ID = "50001234/0b8a6c2e-1f3d-4b5a-9c7e-2d4f6a8b0c1d";

    private final List<String> altinnRequests = Collections.synchronizedList(new ArrayList<>());
    private final List<Map<String, Object>> uploadedFiles = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Function<ClientRequest, Mono<ClientResponse>>> responses = new HashMap<>();

    private IncomingInstanceMappingService service;

    @BeforeEach
    void setUp() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://fint-altinn-service:8080/")
                .exchangeFunction(request -> {
                    String path = request.url().getPath();
                    altinnRequests.add(request.method() + " " + path);
                    return responses.getOrDefault(path, r -> Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build()))
                            .apply(request);
                })
                .build();
        AltinnFileService altinnFileService = new AltinnFileService(webClient);
        service = new IncomingInstanceMappingService(List.of(
                new DrosjeloyveInstanceMapper(altinnFileService),
                new DrosjesentralInstanceMapper(altinnFileService)
        ));
    }

    @Test
    void drosjeloyveWithAllDocuments() throws Exception {
        altinnFiles("ref-data-as-pdf", "politiattest-dagligleder", "skatteattest-dagligleder",
                "konkursattest-dagligleder", "politiattest-foretak", "dom-forelegg", "dokumentasjon-fagkompetanse");
        ebevisFiles("KonkursDrosje", "RestanserV2");

        assertMatchesGolden("altinn-drosjeloyve-all-documents", map(fullInstance("vigo/drosjeloyve")));
    }

    @Test
    void drosjesentralWithAllDocuments() throws Exception {
        altinnFiles("ref-data-as-pdf", "politiattest-dagligleder", "skatteattest-dagligleder",
                "konkursattest-dagligleder", "dom-forelegg", "beskrivelse-yrkestransportloven");
        ebevisFiles("KonkursDrosje", "RestanserV2");

        assertMatchesGolden("altinn-drosjesentral-all-documents", map(fullInstance("vigo/drosjesentral")));
    }

    @Test
    void drosjeloyveWithOnlyMandatoryDocumentsAndOptionalFieldsMissing() throws Exception {
        altinnFiles("ref-data-as-pdf", "politiattest-dagligleder");

        KafkaAltinnInstance instance = KafkaAltinnInstance.builder()
                .instanceId(INSTANCE_ID)
                .appId("vigo/drosjeloyve")
                .organizationNumber("912345678")
                .organizationName("Drosje AS")
                .companyEmail("post@drosje.no")
                .countyName("Østfold")
                .companyAdressStreet("Storgata 1")
                .companyAdressPostcode("1601")
                .companyAdressPostplace("Fredrikstad")
                .build();

        assertMatchesGolden("altinn-drosjeloyve-only-mandatory", map(instance));
    }

    @Test
    void drosjeloyveWithFailingFileRequests() throws Exception {
        altinnFiles("ref-data-as-pdf");
        responses.put(altinnPath("politiattest-dagligleder"), r -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                .body("no filename")
                .build()));
        responses.put(altinnPath("skatteattest-dagligleder"), r -> Mono.error(new IOException("Connection reset")));
        responses.put(altinnPath("konkursattest-dagligleder"), r -> Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"error.txt\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/plain")
                .body("Internal Server Error")
                .build()));
        ebevisFiles("KonkursDrosje");

        assertMatchesGolden("altinn-drosjeloyve-failing-files", map(fullInstance("vigo/drosjeloyve")));
    }

    @Test
    void unknownAppIdIsRejected() throws Exception {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> map(fullInstance("vigo/ukjent")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exception", exception.getClass().getName());
        result.put("message", exception.getMessage());
        result.put("altinnRequests", altinnRequests);
        result.put("uploadedFiles", uploadedFiles);
        assertMatchesGolden("altinn-unknown-app-id", result);
    }

    private Map<String, Object> map(KafkaAltinnInstance instance) {
        InstanceObject instanceObject = service.map(SOURCE_APPLICATION_ID, instance, this::persist);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("altinnRequests", sorted(altinnRequests));
        uploadedFiles.sort(Comparator.comparing(file -> (String) file.get("name")));
        result.put("uploadedFiles", uploadedFiles);
        result.put("instanceObject", instanceObject);
        return result;
    }

    private UUID persist(File file) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", file.getName());
        map.put("sourceApplicationId", file.getSourceApplicationId());
        map.put("sourceApplicationInstanceId", file.getSourceApplicationInstanceId());
        map.put("type", String.valueOf(file.getType()));
        map.put("encoding", file.getEncoding());
        map.put("base64Contents", file.getBase64Contents());
        uploadedFiles.add(map);
        return UUID.nameUUIDFromBytes((file.getName() + "|" + file.getBase64Contents()).getBytes(StandardCharsets.UTF_8));
    }

    private void altinnFiles(String... references) {
        for (String reference : references) {
            responses.put(altinnPath(reference), r -> pdf(reference + ".pdf"));
        }
    }

    private void ebevisFiles(String... evidenceCodeNames) {
        for (String evidenceCodeName : evidenceCodeNames) {
            responses.put("/api/file/ebevis/" + INSTANCE_ID + "/" + evidenceCodeName, r -> pdf(evidenceCodeName + ".pdf"));
        }
    }

    private static String altinnPath(String reference) {
        return "/api/file/" + INSTANCE_ID + "/" + reference;
    }

    private static Mono<ClientResponse> pdf(String filename) {
        return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                .body("%PDF " + filename)
                .build());
    }

    private static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<>(values);
        Collections.sort(copy);
        return copy;
    }

    static KafkaAltinnInstance fullInstance(String appId) {
        return KafkaAltinnInstance.builder()
                .instanceId(INSTANCE_ID)
                .appId(appId)
                .fintOrgId("ofk.no")
                .organizationNumber("912345678")
                .organizationName("Drosje AS")
                .countyNumber("31")
                .countyName("Østfold")
                .municipalityNumber("3107")
                .municipalityName("Fredrikstad")
                .companyEmail("post@drosje.no")
                .companyPhone("69000000")
                .companyAdressStreet("Storgata 1")
                .companyAdressPostcode("1601")
                .companyAdressPostplace("Fredrikstad")
                .postalAdressStreet("Postboks 10")
                .postalAdressPostcode("1602")
                .postalAdressPostplace("Fredrikstad")
                .managerSocialSecurityNumber("01010112345")
                .managerFirstName("Kari")
                .managerLastName("Nordmann")
                .managerEmail("kari@drosje.no")
                .managerPhone("90000000")
                .transportmanagerSocialSecurityNumber("02020212345")
                .transportmanagerFirstName("Ola")
                .transportmanagerLastName("Nordmann")
                .transportmanagerEmail("ola@drosje.no")
                .transportmanagerPhone("91000000")
                .transportmanagerAffiliation("Ansatt")
                .build();
    }
}
