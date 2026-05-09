package cn.iocoder.yudao.module.system.dal.mysql;

import cn.iocoder.yudao.module.system.dal.dataobject.SystemMenuDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 菜单权限 Mapper。
 */
@Mapper
public interface SystemMenuMapper extends BaseMapper<SystemMenuDO> {

    default List<SystemMenuDO> selectListByQuery(String name, Integer status) {
        return selectList(Wrappers.lambdaQuery(SystemMenuDO.class)
                .like(StringUtils.isNotBlank(name), SystemMenuDO::getName, name)
                .eq(status != null, SystemMenuDO::getStatus, status)
                .eq(SystemMenuDO::getDeleted, false)
                .orderByAsc(SystemMenuDO::getSort)
                .orderByAsc(SystemMenuDO::getId));
    }

    default List<SystemMenuDO> selectEnabledMenus() {
        return selectList(Wrappers.lambdaQuery(SystemMenuDO.class)
                .eq(SystemMenuDO::getDeleted, false)
                .eq(SystemMenuDO::getStatus, 0)
                .orderByAsc(SystemMenuDO::getSort)
                .orderByAsc(SystemMenuDO::getId));
    }

}
