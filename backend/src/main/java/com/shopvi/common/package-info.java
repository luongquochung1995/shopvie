/**
 * Hạ tầng dùng chung (config, security, exception, response wrapper, util).
 * Module OPEN: mọi module nghiệp vụ được phép dùng các type bên trong.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.shopvi.common;

import org.springframework.modulith.ApplicationModule;
