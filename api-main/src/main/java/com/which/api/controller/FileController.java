package com.which.api.controller;

import cn.hutool.core.io.FileUtil;
import com.which.api.manager.OssManager;
import com.which.api.service.UserService;
import com.which.apicommon.common.BaseResponse;
import com.which.apicommon.common.BusinessException;
import com.which.apicommon.common.ErrorCode;
import com.which.apicommon.common.ResultUtils;
import com.which.apicommon.model.dto.file.UploadFileRequest;
import com.which.apicommon.model.emums.FileUploadBizEnum;
import com.which.apicommon.model.emums.ImageStatusEnum;
import com.which.apicommon.model.vo.ImageVO;
import com.which.apicommon.model.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.time.LocalDateTime;
import java.util.Arrays;

import static com.which.apicommon.constant.CommonConstant.ONE_MB;
import static com.which.apicommon.constant.CommonConstant.OSS_HOST;
import static com.which.apicommon.constant.UserConstant.DEMO_ROLE;

/**
 * 文件接口
 *
 * @author which
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private UserService userService;

    @Resource
    private OssManager ossManager;

    /**
     * 上传文件
     *
     * @param multipartFile
     * @param uploadFileRequest
     * @param request
     * @return
     */
    @PostMapping("/upload")
    public BaseResponse<ImageVO> uploadFile(@RequestPart("file") MultipartFile multipartFile, UploadFileRequest uploadFileRequest, HttpServletRequest request) {
        String biz = uploadFileRequest.getBiz();
        FileUploadBizEnum fileUploadBizEnum = FileUploadBizEnum.getEnumByValue(biz);
        ImageVO imageVO = new ImageVO();
        imageVO.setUpdateTime(LocalDateTime.now());
        if (fileUploadBizEnum == null) {
            return uploadError(imageVO, multipartFile, "上传失败，请重试.");
        }
        String result = validFile(multipartFile, fileUploadBizEnum);
        if (!"success".equals(result)) {
            return uploadError(imageVO, multipartFile, result);
        }
        UserVO loginUser = userService.getLoginUser(request);
        // 演示账号无权限
        if (DEMO_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 文件目录：根据业务、用户来划分
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String filename = uuid + "-" + multipartFile.getOriginalFilename();
        String filepath = String.format("%s/%s/%s", fileUploadBizEnum.getValue(), loginUser.getId(), filename);
        File file = null;

        try {
            // 上传文件
            file = File.createTempFile(filepath, null);
            multipartFile.transferTo(file);
            ossManager.putObject(filepath, file);
            imageVO.setName(multipartFile.getOriginalFilename());
            imageVO.setUid(RandomStringUtils.randomAlphanumeric(8));
            imageVO.setStatus(ImageStatusEnum.SUCCESS.getValue());
            imageVO.setUrl(OSS_HOST + filepath);
            // 返回可访问地址
            return ResultUtils.success(imageVO);
        } catch (Exception e) {
            log.error("file upload error, filepath = " + filepath, e);
            return uploadError(imageVO, multipartFile, "上传失败，请重试");
        } finally {
            if (file != null) {
                // 删除临时文件
                boolean delete = file.delete();
                if (!delete) {
                    log.error("file delete error, filepath = {}", filepath);
                }
            }
        }
    }

    /**
     * 处理上传失败
     *
     * @param imageVO
     * @param multipartFile
     * @param message
     * @return
     */
    private BaseResponse<ImageVO> uploadError(ImageVO imageVO, MultipartFile multipartFile, String message) {
        imageVO.setName(multipartFile.getOriginalFilename());
        imageVO.setUid(RandomStringUtils.randomAlphanumeric(8));
        imageVO.setStatus(ImageStatusEnum.ERROR.getValue());
        return ResultUtils.error(imageVO, ErrorCode.OPERATION_ERROR, message);
    }

    /**
     * 有效文件
     * 校验文件
     *
     * @param fileUploadBizEnum
     * @param multipartFile
     */
    private String validFile(MultipartFile multipartFile, FileUploadBizEnum fileUploadBizEnum) {
        // 文件大小
        long fileSize = multipartFile.getSize();
        // 文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        if (FileUploadBizEnum.USER_AVATAR.equals(fileUploadBizEnum)) {
            if (fileSize > ONE_MB) {
                return "图片大小不能超过1M";
            }
            if (!Arrays.asList("jpeg", "jpg", "svg", "png", "webp", "jiff").contains(fileSuffix)) {
                return "图片格式错误";
            }
        }
        return "success";
    }

}
