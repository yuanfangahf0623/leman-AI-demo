package cn.iocoder.yudao.module.dataplatform.service.warehouse;

import cn.iocoder.yudao.module.dataplatform.controller.admin.warehouse.vo.WarehouseColumnRespVO;
import cn.iocoder.yudao.module.dataplatform.controller.admin.warehouse.vo.WarehouseTableRespVO;

import java.util.List;
import java.util.Map;

public interface DataWarehouseService {
    Map<String, Object> health();
    List<String> listDatabases();
    List<WarehouseTableRespVO> listTables(String database);
    List<WarehouseColumnRespVO> listColumns(String database, String table);
}
