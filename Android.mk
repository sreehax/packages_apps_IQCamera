LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
ifeq ($(TARGET_BUILD_APPS),)
support_library_root_dir := frameworks/support
else
support_library_root_dir := prebuilts/sdk/current/support
endif
src_dirs := src
res_dirs := res 
LOCAL_SRC_FILES := $(call all-java-files-under, $(src_dirs))
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs)) \
    $(support_library_root_dir)/design/res \
    $(support_library_root_dir)/transition/res \
    $(support_library_root_dir)/v7/appcompat/res \
    $(support_library_root_dir)/v7/cardview/res \
    $(support_library_root_dir)/v7/recyclerview/res

LOCAL_MODULE_TAGS := optional
LOCAL_STATIC_JAVA_LIBRARIES := \
    android-common \
    android-support-design \
    android-support-transition \
    android-support-v13 \
    android-support-v7-appcompat \
    android-support-v7-cardview \
    android-support-v7-recyclerview \
    android-support-v7-palette \
    android-support-v4
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res
LOCAL_PACKAGE_NAME := IQCamera
LOCAL_PRIVILEGED_MODULE := true
#LOCAL_USE_AAPT2 := false
LOCAL_AAPT_FLAGS := \
    --auto-add-overlay \
    --extra-packages android.support.design \
    --extra-packages android.support.transition \
    --extra-packages android.support.v7.appcompat \
    --extra-packages android.support.v7.cardview \
--extra-packages android.support.v7.recyclerview
LOCAL_PRIVILEGED_MODULE := true
include $(BUILD_PACKAGE)
include $(CLEAR_VARS)
