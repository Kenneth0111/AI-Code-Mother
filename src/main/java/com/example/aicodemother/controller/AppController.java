package com.example.aicodemother.controller;

import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.aicodemother.model.entity.App;
import com.example.aicodemother.service.AppService;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 应用 控制层。
 *
 * @author <a href="https://github.com/Kenneth0111">程序员张博洋</a>
 */
@RestController
@RequestMapping("/app")
public class AppController {

    @Resource
    private AppService appService;



}
