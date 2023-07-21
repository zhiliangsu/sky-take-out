package com.sky.service;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.SetmealVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface SetmealService {
    /**
     * 新增套餐, 同时需要保存套餐和菜品的关联关系
     *
     * @param setmealDTO
     * @return
     */
    void saveWithDishes(SetmealDTO setmealDTO);

    /**
     * 套餐分页查询
     *
     * @param setmealPageQueryDTO
     * @return
     */
    PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * 批量删除套餐
     *
     * @param ids
     * @return
     */
    void deleteBatch(List<Long> ids);

    /**
     * 根据id查询套餐和关联的菜品数据
     *
     * @param id
     * @return
     */
    SetmealVO getByIdWithDish(Long id);

    /**
     * 修改套餐
     *
     * @param setmealDTO
     * @return
     */
    void updateWithDish(SetmealDTO setmealDTO);

    /**
     * 起售或停售套餐
     *
     * @param status
     * @param id
     * @return
     */
    void startOrStop(Integer status, Long id);
}
