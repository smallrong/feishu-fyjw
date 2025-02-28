package em.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import em.backend.pojo.CaseInfo;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Mapper
public interface CaseInfoMapper extends BaseMapper<CaseInfo> {
    // 基础的 CRUD 操作由 BaseMapper 提供
} 