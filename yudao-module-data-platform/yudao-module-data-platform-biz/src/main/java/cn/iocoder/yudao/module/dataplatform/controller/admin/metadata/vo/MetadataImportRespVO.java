package cn.iocoder.yudao.module.dataplatform.controller.admin.metadata.vo;

import java.util.List;

public record MetadataImportRespVO(int totalRows, int updatedRows, int skippedRows, List<String> errors) {
}
