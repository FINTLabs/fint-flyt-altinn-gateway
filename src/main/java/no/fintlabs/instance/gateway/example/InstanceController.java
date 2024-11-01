package no.fintlabs.instance.gateway.example;

import no.fintlabs.gateway.instance.InstanceProcessor;
import no.fintlabs.instance.gateway.example.collectionandfiles.IncomingInstanceWithCollectionOfFiles;
import no.fintlabs.instance.gateway.example.simple.IncomingInstance;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static no.fintlabs.resourceserver.UrlPaths.EXTERNAL_API;

@RestController
@RequestMapping(EXTERNAL_API + "/sourceapplication/instances")
public class InstanceController {

    private final InstanceProcessor<IncomingInstance> simpleInstanceProcessor;
    private final InstanceProcessor<IncomingInstanceWithCollectionOfFiles> instanceWithCollectionAndFilesProcessor;

    public InstanceController(
            InstanceProcessor<IncomingInstance> simpleInstanceProcessor,
            InstanceProcessor<IncomingInstanceWithCollectionOfFiles> instanceWithCollectionAndFilesProcessor
    ) {
        this.simpleInstanceProcessor = simpleInstanceProcessor;
        this.instanceWithCollectionAndFilesProcessor = instanceWithCollectionAndFilesProcessor;
    }


    @PostMapping("instance")
    public Mono<ResponseEntity<?>> postIncomingInstance(
            @RequestBody IncomingInstance incomingInstance,
            @AuthenticationPrincipal Mono<Authentication> authenticationMono
    ) {
        return authenticationMono.flatMap(
                authentication -> simpleInstanceProcessor.processInstance(
                        authentication,
                        incomingInstance
                )
        );
    }

    @PostMapping("instancewithcollectionandfiles")
    public Mono<ResponseEntity<?>> postIncomingInstanceWithCollectionElements(
            @RequestBody IncomingInstanceWithCollectionOfFiles incomingInstanceWithCollectionOfFiles,
            @AuthenticationPrincipal Mono<Authentication> authenticationMono
    ) {
        return authenticationMono.flatMap(
                authentication -> instanceWithCollectionAndFilesProcessor.processInstance(
                        authentication,
                        incomingInstanceWithCollectionOfFiles
                )
        );
    }

}
