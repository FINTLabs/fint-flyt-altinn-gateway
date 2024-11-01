package no.fintlabs.instance.gateway.example.collectionandfiles;

import no.fintlabs.gateway.instance.InstanceMapper;
import no.fintlabs.gateway.instance.model.File;
import no.fintlabs.gateway.instance.model.instance.InstanceObject;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.Function;

@Service
public class IncomingInstanceWithCollectionOfFilesMappingService implements InstanceMapper<IncomingInstanceWithCollectionOfFiles> {

    @Override
    public Mono<InstanceObject> map(
            Long sourceApplicationId,
            IncomingInstanceWithCollectionOfFiles incomingInstanceWithCollectionOfFiles,
            Function<File, Mono<UUID>> persistFile
    ) {
        return mapCollectionElementsToInstanceObjects(
                persistFile,
                sourceApplicationId,
                incomingInstanceWithCollectionOfFiles.getStringValue1(),
                incomingInstanceWithCollectionOfFiles.getElementCollection1()
        ).map((List<InstanceObject> collectionElementsAsInstanceObjects) -> {
                    HashMap<String, String> valuePerKey = getStringStringHashMap(incomingInstanceWithCollectionOfFiles);
                    return InstanceObject.builder()
                            .valuePerKey(valuePerKey)
                            .objectCollectionPerKey(
                                    Map.of(
                                            "exampleCollectionObjects", collectionElementsAsInstanceObjects
                                    ))
                            .build();
                }
        );
    }

    private static HashMap<String, String> getStringStringHashMap(IncomingInstanceWithCollectionOfFiles incomingInstanceWithCollectionOfFiles) {
        HashMap<String, String> valuePerKey = new HashMap<>();
        valuePerKey.put("exampleIntegerValue1Key", String.valueOf(incomingInstanceWithCollectionOfFiles.getIntegerValue1()));
        valuePerKey.put("exampleStringValue1Key", incomingInstanceWithCollectionOfFiles.getStringValue1());
        valuePerKey.put("exampleStringValue2Key", Optional.ofNullable(incomingInstanceWithCollectionOfFiles.getStringValue2()).orElse(""));
        valuePerKey.put("exampleStringValue3Key", Optional.ofNullable(incomingInstanceWithCollectionOfFiles.getStringValue3()).orElse(""));
        return valuePerKey;
    }

    private Mono<List<InstanceObject>> mapCollectionElementsToInstanceObjects(
            Function<File, Mono<UUID>> persistFile,
            Long sourceApplicationId,
            String sourceApplicationInstanceId,
            List<IncomingInstanceCollectionElement> incomingInstanceCollectionElements
    ) {
        return Flux.fromIterable(incomingInstanceCollectionElements)
                .flatMap(incomingInstanceCollectionElement -> postFile(
                                persistFile,
                                sourceApplicationId,
                                sourceApplicationInstanceId,
                                incomingInstanceCollectionElement
                        ).map(fileId -> mapCollectionElementAndFileIdToInstanceObject(
                                incomingInstanceCollectionElement,
                                getMediaType(incomingInstanceCollectionElement),
                                fileId))
                )
                .collectList();
    }

    private MediaType getMediaType(IncomingInstanceCollectionElement incomingInstanceCollectionElement) {
        return MediaTypeFactory.getMediaType(incomingInstanceCollectionElement.getFilename())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No media type found for fileName=" + incomingInstanceCollectionElement.getFilename()
                ));
    }

    private Mono<UUID> postFile(
            Function<File, Mono<UUID>> persistFile,
            Long sourceApplicationId,
            String sourceApplicationInstanceId,
            IncomingInstanceCollectionElement incomingInstanceCollectionElement
    ) {
        MediaType mediaType = getMediaType(incomingInstanceCollectionElement);
        File file = toFile(
                sourceApplicationId,
                sourceApplicationInstanceId,
                incomingInstanceCollectionElement,
                mediaType
        );
        return persistFile.apply(file);
    }

    private InstanceObject mapCollectionElementAndFileIdToInstanceObject(
            IncomingInstanceCollectionElement incomingInstanceCollectionElement,
            MediaType mediaType,
            UUID fileId
    ) {

        return InstanceObject
                .builder()
                .valuePerKey(Map.of(
                        "exampleStringValueKey", Optional.ofNullable(incomingInstanceCollectionElement.getStringValue()).orElse(""),
                        "filenameKey", Optional.ofNullable(incomingInstanceCollectionElement.getFilename()).orElse(""),
                        "mediatypeKey", mediaType.toString(),
                        "fileKey", fileId.toString()
                ))
                .build();
    }

    private File toFile(
            Long sourceApplicationId,
            String sourceApplicationInstanceId,
            IncomingInstanceCollectionElement incomingInstanceCollectionElement,
            MediaType type
    ) {
        return File
                .builder()
                .name(incomingInstanceCollectionElement.getFilename())
                .type(type)
                .sourceApplicationId(sourceApplicationId)
                .sourceApplicationInstanceId(sourceApplicationInstanceId)
                .encoding("UTF-8")
                .base64Contents(incomingInstanceCollectionElement.getFileContentBase64())
                .build();
    }

}
