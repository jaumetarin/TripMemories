package es.upv.pin.backend.dto;

import java.util.List;

public record ContenidoAlbumDto(AlbumDto album, List<FotoDto> fotos, LimitesDto limites, long bytesUsados) {
    public record LimitesDto(List<String> formatos, long maxArchivoBytes, int maxFotosAlbum,
            long cuotaBytes, long maxPixeles) {}
}
