package org.javarosa.test_utils

import java.io.File
import java.io.IOException
import java.lang.reflect.Modifier
import java.net.URI
import java.net.URISyntaxException

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
class ClassLoadUtils {
    companion object {
        /**
         * Filter classes such that they extend the base class and are not abstract
         */
        @JvmStatic
        fun classesThatExtend(classes: Set<Class<*>>, baseClass: Class<*>): List<Class<*>> {
            val filteredClasses = ArrayList<Class<*>>()
            for (cls in classes) {
                if (baseClass.isAssignableFrom(cls)
                    && !Modifier.isAbstract(cls.modifiers)
                ) {
                    filteredClasses.add(cls)
                }
            }
            return filteredClasses
        }

        /**
         * Scans all classes accessible from the context class loader which belong
         * to the given package and subpackages.
         *
         * via http://stackoverflow.com/a/862130
         */
        @JvmStatic
        @Throws(ClassNotFoundException::class, IOException::class, URISyntaxException::class)
        fun getClasses(packageName: String): Set<Class<*>> {
            val classLoader = Thread.currentThread().contextClassLoader
            val path = packageName.replace('.', '/')
            val resources = classLoader.getResources(path).toList().iterator()
            val dirs = ArrayList<File>()
            while (resources.hasNext()) {
                val resource = resources.next()
                val uri = URI(resource.toString())
                if (uri.path != null) {
                    dirs.add(File(uri.path))
                }
            }
            val classes = HashSet<Class<*>>()
            for (directory in dirs) {
                classes.addAll(findClasses(directory, packageName))
            }

            return classes
        }

        /**
         * Recursive method used to find all classes in a given directory and
         * subdirs.
         *
         * via http://stackoverflow.com/a/862130
         *
         * @param directory   The base directory
         * @param packageName The package name for classes found inside the base directory
         */
        @Throws(ClassNotFoundException::class)
        private fun findClasses(directory: File, packageName: String): List<Class<*>> {
            val classes = ArrayList<Class<*>>()
            if (!directory.exists()) {
                return classes
            }
            val files = directory.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory) {
                        classes.addAll(findClasses(file, packageName + "." + file.name))
                    } else if (file.name.endsWith(".class")) {
                        val fileName = file.name.substring(0, file.name.length - 6)
                        classes.add(Class.forName("$packageName.$fileName"))
                    }
                }
            }
            return classes
        }
    }
}
