package io.muun.apollo.data.libwallet.grpc;

import android.net.LocalSocketAddress;
import io.grpc.EquivalentAddressGroup;
import io.grpc.ManagedChannel;
import io.grpc.NameResolver;
import io.grpc.android.UdsChannelBuilder;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;

public class GrpcChannelFactory {

    private GrpcChannelFactory() {
    }

    /**
     * Create a gRPC channel using the given socket path.
     */
    public static ManagedChannel create(String socketPath) {
        return UdsChannelBuilder
                .forPath(socketPath, LocalSocketAddress.Namespace.FILESYSTEM)
                // On Android 15, when our app is in background, a DNS call
                // (which includes InetAddress.getAllByName("localhost")) could be blocked, throwing
                // UnknownHostException.
                // By using our UdsNameResolverFactory() we override the default DNS resolver.
                // Instead, our factory returns a FailFastNameResolver, which skips all DNS lookups
                // by immediately emitting a single unresolved address.
                // This allows that gRPC opens the Unix domain socket without calling DNS.
                .nameResolverFactory(new UdsNameResolverFactory())
                .build();
    }

    private static class UdsNameResolverFactory extends NameResolver.Factory {

        @Override
        public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
            // Ignore the targetUri and always use our FailFastNameResolver.
            return new FailFastNameResolver();
        }

        @Override
        public String getDefaultScheme() {
            // UdsChannelBuilder uses the "dns" scheme by default, so by returning "dns" here we
            // ensure gRPC will select this factory for our UDS channel instead of the built-in
            // DNS resolver.
            return "dns";
        }
    }

    private static class FailFastNameResolver extends NameResolver {

        @Override
        public String getServiceAuthority() {
            return "localhost";
        }

        @Override
        public void start(Listener2 listener) {
            // Bypass DNS resolution by emitting a single unresolved address group.
            // The UDS transport ignores the host/port ("", 0) and uses the configured socket
            // path directly.
            final var addresses = List.of(
                    new EquivalentAddressGroup(InetSocketAddress.createUnresolved("", 0))
            );
            final var resolutionResult = ResolutionResult
                    .newBuilder()
                    .setAddresses(addresses)
                    .build();

            listener.onResult(resolutionResult);
        }

        @Override
        public void shutdown() {
            // Do nothing
        }
    }
}
