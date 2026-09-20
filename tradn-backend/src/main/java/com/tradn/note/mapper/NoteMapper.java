package com.tradn.note.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tradn.note.model.Note;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 笔记主表数据访问接口。 */
public interface NoteMapper extends BaseMapper<Note> {
    /** 查询当前用户绑定指定标签的笔记 ID，用于保持分页查询在数据库侧完成。 */
    @Select(
            "SELECT r.note_id FROM note_tag_relation r "
                    + "INNER JOIN note_tag t ON t.id = r.tag_id "
                    + "INNER JOIN note n ON n.id = r.note_id "
                    + "WHERE t.user_id = #{userId} AND t.id = #{tagId} "
                    + "AND t.deleted = 0 AND n.user_id = #{userId} AND n.deleted = 0")
    List<Long> selectIdsByTag(
            @Param("userId") Long userId,
            @Param("tagId") Long tagId);
}
