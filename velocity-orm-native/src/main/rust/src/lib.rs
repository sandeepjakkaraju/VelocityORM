use jni::JNIEnv;
use jni::objects::{JClass, JObject, JString, JByteArray, JLongArray, JValue};
use jni::sys::{jlong, jint, jboolean, jstring};
use once_cell::sync::Lazy;
use dashmap::DashMap;
use xxhash_rust::xxh3::xxh3_64;
use chrono::NaiveDateTime;

/// Native L2 Cache - Off-heap storage for entity hashes and dirty states.
static DIRTY_STATE_CACHE: Lazy<DashMap<jlong, jlong>> = Lazy::new(|| DashMap::new());

#[no_mangle]
pub extern "system" fn Java_com_velocityorm_nativeorm_NativeRowIdentity_calculateRowHashNative(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jlong {
    let input_str: String = match env.get_string(&input) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };
    let hash = xxh3_64(input_str.as_bytes());
    hash as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_velocityorm_nativeorm_NativeRowIdentity_calculateBulkHashNative(
    mut env: JNIEnv,
    _class: JClass,
    data: JByteArray,
) -> jlong {
    let bytes = match env.convert_byte_array(&data) {
        Ok(b) => b,
        Err(_) => return 0,
    };
    let hash = xxh3_64(&bytes);
    hash as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_velocityorm_nativeorm_NativeCRUDOptimizer_markCleanNative(
    _env: JNIEnv,
    _class: JClass,
    entity_id: jlong,
    hash: jlong,
) {
    DIRTY_STATE_CACHE.insert(entity_id, hash);
}

#[no_mangle]
pub extern "system" fn Java_com_velocityorm_nativeorm_NativeCRUDOptimizer_isDirtyNative(
    _env: JNIEnv,
    _class: JClass,
    entity_id: jlong,
    current_hash: jlong,
) -> jboolean {
    if let Some(old_hash) = DIRTY_STATE_CACHE.get(&entity_id) {
        if *old_hash == current_hash {
            return 0; // Not dirty (false)
        }
    }
    1 // Dirty (true)
}

#[no_mangle]
pub extern "system" fn Java_com_velocityorm_nativeorm_NativeCRUDOptimizer_injectStringFieldNative(
    mut env: JNIEnv,
    _class: JClass,
    target: JObject,
    field_name: JString,
    value: JString,
) {
    let name: String = env.get_string(&field_name).unwrap().into();
    let val_obj = JObject::from(value);
    // In jni 0.21.1, set_field takes (obj, name, sig, value)
    let _ = env.set_field(&target, &name, "Ljava/lang/String;", JValue::from(&val_obj));
}

#[no_mangle]
pub extern "system" fn Java_com_velocityorm_nativeorm_NativeCRUDOptimizer_injectLongFieldNative(
    mut env: JNIEnv,
    _class: JClass,
    target: JObject,
    field_name: JString,
    value: jlong,
) {
    let name: String = env.get_string(&field_name).unwrap().into();
    let _ = env.set_field(&target, &name, "J", JValue::from(value));
}

#[no_mangle]
pub extern "system" fn Java_com_velocityorm_nativeorm_NativeCRUDOptimizer_fastJoinIdsNative(
    mut env: JNIEnv,
    _class: JClass,
    ids: JLongArray,
) -> jstring {
    let ids_vec = unsafe { env.get_array_elements(&ids, jni::objects::ReleaseMode::NoCopyBack).unwrap() };
    let mut result = String::with_capacity(ids_vec.len() * 10);
    
    for (i, id) in ids_vec.iter().enumerate() {
        result.push_str(&id.to_string());
        if i < ids_vec.len() - 1 {
            result.push(',');
        }
    }
    
    env.new_string(result).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_velocityorm_nativeorm_NativeDataProcessor_decodeLongBatchNative(
    mut env: JNIEnv,
    _class: JClass,
    input: JByteArray,
    count: jint,
) -> jni::sys::jlongArray {
    let bytes = match env.convert_byte_array(&input) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };

    let count = count as usize;
    let mut result = Vec::with_capacity(count);

    for i in 0..count {
        let start = i * 8;
        if start + 8 <= bytes.len() {
            let mut arr = [0u8; 8];
            arr.copy_from_slice(&bytes[start..start + 8]);
            result.push(i64::from_be_bytes(arr));
        }
    }

    let j_array = env.new_long_array(result.len() as jint).unwrap();
    env.set_long_array_region(&j_array, 0, &result).unwrap();
    j_array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_velocityorm_nativeorm_NativeDataProcessor_buildBulkInsertValuesNative(
    mut env: JNIEnv,
    _class: JClass,
    rows: jint,
    cols: jint,
) -> jstring {
    let rows = rows as usize;
    let cols = cols as usize;
    let mut sql = String::with_capacity(rows * (cols * 3));
    
    for r in 0..rows {
        sql.push('(');
        for c in 0..cols {
            sql.push('?');
            if c < cols - 1 {
                sql.push(',');
            }
        }
        sql.push(')');
        if r < rows - 1 {
            sql.push(',');
        }
    }

    env.new_string(sql).unwrap().into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_velocityorm_nativeorm_NativeDataProcessor_parseIsoTimestampsNative(
    mut env: JNIEnv,
    _class: JClass,
    input: jni::objects::JObjectArray,
) -> jni::sys::jlongArray {
    let len = env.get_array_length(&input).unwrap();
    let mut result = Vec::with_capacity(len as usize);

    for i in 0..len {
        let obj = env.get_object_array_element(&input, i).unwrap();
        let j_str: JString = obj.into();
        let r_str: String = env.get_string(&j_str).unwrap().into();
        
        if let Ok(dt) = NaiveDateTime::parse_from_str(&r_str, "%Y-%m-%dT%H:%M:%S") {
            result.push(dt.and_utc().timestamp_millis());
        } else {
            result.push(0);
        }
    }

    let j_array = env.new_long_array(result.len() as jint).unwrap();
    env.set_long_array_region(&j_array, 0, &result).unwrap();
    j_array.into_raw()
}
