package core;
import annotations.MyController;
import annotations.MyRequestMapping;
import annotations.MyRequestParam;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;


public class MyDispatcherServlet extends HttpServlet {
    private Properties properties = new Properties();

    private List<String> classNames = new ArrayList<String>();

    private Map<String, Object> ioc = new HashMap<String, Object>();

    private Map<String, Method> handlerMapping = new  HashMap<String, Method>();

    private Map<String, Object> controllerMap  =new HashMap<String, Object>();

    @Override
    public void init(ServletConfig servletConfig) throws ServletException{
        doLoadConfig((servletConfig.getInitParameter("contextConfigLocation")));
        doScanner(properties.getProperty("scanPackage"));
        doInstance();
        System.out.println("ccc");
        try {
            initHandlerMapping();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, IOException {
        try {
            //处理请求
            doDispatch(req,resp);
        } catch (Exception e) {
            resp.getWriter().write("500!! Server Exception");
        }

    }
    public void doDispatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String url =request.getRequestURI();
        String contextPath = request.getContextPath();

        url=url.replace(contextPath, "").replaceAll("/+", "/");

        if(!this.handlerMapping.containsKey(url)){
            System.out.println(url);
            for (Map.Entry<String, Method> entry: handlerMapping.entrySet()) {
                System.out.println(entry.getKey()+" "+entry.getValue().getName());
            }
//            System.out.println("asd");
            response.getWriter().write("404 NOT FOUND!");
            return;
        }

        Method method=handlerMapping.get(url);

        Class<?>[] parameterTypes =method.getParameterTypes();

        Parameter[] parameters=method.getParameters();

        Object[] paramValues=new Object[parameterTypes.length];

        for(int i=0;i<parameterTypes.length;i++){
            System.out.println("zzz:");
            System.out.println(parameterTypes[i].getName());
            System.out.println(parameterTypes[i].isAnnotationPresent(MyRequestParam.class));
            System.out.println(parameters[i].isAnnotationPresent(MyRequestParam.class));
            if(parameterTypes[i].getSimpleName().equals("HttpServletRequest")){
                paramValues[i]=request;
            }
            else if(parameterTypes[i].getSimpleName().equals("HttpServletResponse")){
                paramValues[i]=response;
            }

            else if(parameters[i].isAnnotationPresent(MyRequestParam.class)){
                String key=parameters[i].getAnnotation(MyRequestParam.class).value();
                paramValues[i]=request.getParameter(key);
            }

        }


        try{
            method.invoke(controllerMap.get(url),paramValues);
        }catch (Exception e){
            e.printStackTrace();
        }

    }


    private void doLoadConfig(String location){
        InputStream in=this.getClass().getClassLoader().getResourceAsStream(location);
//        System.out.println();
//        System.out.println(location);
//        System.out.println();
        try{
           properties.load(in);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(in!=null){
                try{
                    in.close();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    private void doScanner(String packageName){
        URL url=this.getClass().getClassLoader().getResource("/"+packageName.replaceAll("\\.","/"));
//        System.out.println(packageName);
//        System.out.println("/"+packageName.replaceAll("\\.","/"));
        File dir= new File(url.getFile());
        for(File file:dir.listFiles()){

            System.out.println("file;"+file.getName());
            if(file.isDirectory()){
                doScanner(packageName+"."+file.getName());
            }else{
                String className=packageName+"."+file.getName().replaceAll(".class","");
                classNames.add(className);
            }
        }
    }

    private String toLowerFirstWord(String name){
        char[] charArray = name.toCharArray();
        charArray[0] += 32;
        return String.valueOf(charArray);
    }
    private void doInstance() {
        if (classNames.isEmpty()) {
            System.out.println("classNames.isEmpty()");
            return;
        }
        for (String className : classNames) {
            try {
                //把类搞出来,反射来实例化(只有加@MyController需要实例化)
                Class<?> clazz =Class.forName(className);
                if(clazz.isAnnotationPresent(MyController.class)){
                    ioc.put(toLowerFirstWord(clazz.getSimpleName()),clazz.newInstance());
                }else{
                    continue;
                }

            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
        }
    }


    private void initHandlerMapping() throws IllegalAccessException, InstantiationException {
        if(ioc.isEmpty())  {
            return;
        }

        for(Map.Entry<String,Object> entry:ioc.entrySet()  ){

            Class<?> clazz=entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(MyController.class)){
                continue;
            }

            String baseUrl="";

            if(clazz.isAnnotationPresent(MyRequestMapping.class)){
                MyRequestMapping myRequestMapper=clazz.getAnnotation(MyRequestMapping.class);

                baseUrl=myRequestMapper.value();

            }

            Method[] methods=clazz.getMethods();

            for(Method method:methods){

                if(method.isAnnotationPresent(MyRequestMapping.class)){
                    MyRequestMapping annotation=method.getAnnotation(MyRequestMapping.class);
                    String url = annotation.value();

                    url =(baseUrl+"/"+url).replaceAll("/+", "/");
                    handlerMapping.put(url,method);
                    controllerMap.put(url,clazz.newInstance());
                }

            }



        }

//        if(ioc.isEmpty()){
//            System.out.println("ioc.isEmpty()");
//            return;
//        }
//        try {
//            System.out.println("zzzz");
//            for (Map.Entry<String, Object> entry: ioc.entrySet()) {
//                Class<? extends Object> clazz = entry.getValue().getClass();
//                System.out.println(clazz.getName()+" "+clazz.isAnnotationPresent(MyController.class)+" "+clazz.isAnnotationPresent(MyRequestMapping.class));
//                if(!clazz.isAnnotationPresent(MyController.class)){
//                    continue;
//                }
//
//                //拼url时,是controller头的url拼上方法上的url
//                String baseUrl ="";
//                if(clazz.isAnnotationPresent(MyRequestMapping.class)){
//                    MyRequestMapping annotation = clazz.getAnnotation(MyRequestMapping.class);
//                    baseUrl=annotation.value();
//                }
//                Method[] methods = clazz.getMethods();
//                for (Method method : methods) {
//                    System.out.println(method.getName());
//                    if(!method.isAnnotationPresent(MyRequestMapping.class)){
//                        continue;
//                    }
//                    MyRequestMapping annotation = method.getAnnotation(MyRequestMapping.class);
//                    String url = annotation.value();
//
//                    url =(baseUrl+"/"+url).replaceAll("/+", "/");
//                    handlerMapping.put(url,method);
//                    controllerMap.put(url,clazz.newInstance());
//                    System.out.println(url+","+method);
//                }
//
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

    }


}
