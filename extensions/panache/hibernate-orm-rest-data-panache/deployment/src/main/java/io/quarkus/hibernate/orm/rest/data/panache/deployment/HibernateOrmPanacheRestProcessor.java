package io.quarkus.hibernate.orm.rest.data.panache.deployment;

import static io.quarkus.deployment.builditem.FeatureBuildItem.HIBERNATE_ORM_REST_DATA_PANACHE;

import java.lang.reflect.Modifier;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;

import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.hibernate.orm.rest.data.panache.PanacheEntityResource;
import io.quarkus.hibernate.orm.rest.data.panache.PanacheRepositoryResource;
import io.quarkus.rest.data.panache.deployment.RestDataResourceBuildItem;
import io.quarkus.rest.data.panache.deployment.RestDataResourceInfo;

class HibernateOrmPanacheRestProcessor {

    private static final DotName PANACHE_ENTITY_RESOURCE_INTERFACE = DotName
            .createSimple(PanacheEntityResource.class.getName());

    private static final DotName PANACHE_REPOSITORY_RESOURCE_INTERFACE = DotName
            .createSimple(PanacheRepositoryResource.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(HIBERNATE_ORM_REST_DATA_PANACHE);
    }

    @BuildStep
    void findEntityResources(CombinedIndexBuildItem index, BuildProducer<RestDataResourceBuildItem> resourcesProducer) {
        for (ClassInfo classInfo : index.getIndex().getKnownDirectImplementors(PANACHE_ENTITY_RESOURCE_INTERFACE)) {
            validateResource(index.getIndex(), classInfo);
            List<Type> generics = getGenericTypes(classInfo);
            String entityClassName = generics.get(0).toString();
            String idClassName = generics.get(1).toString();
            RestDataResourceInfo resourceInfo = HibernateOrmRestDataResourceInfo
                    .withEntityAccess(classInfo, idClassName, entityClassName);
            resourcesProducer.produce(new RestDataResourceBuildItem(resourceInfo));
        }
    }

    @BuildStep
    void findRepositoryResources(CombinedIndexBuildItem index,
            BuildProducer<RestDataResourceBuildItem> resourcesProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeansProducer) {
        for (ClassInfo classInfo : index.getIndex().getKnownDirectImplementors(PANACHE_REPOSITORY_RESOURCE_INTERFACE)) {
            validateResource(index.getIndex(), classInfo);
            List<Type> generics = getGenericTypes(classInfo);
            String repositoryClassName = generics.get(0).toString();
            String entityClassName = generics.get(1).toString();
            String idClassName = generics.get(2).toString();
            RestDataResourceInfo resourceInfo = HibernateOrmRestDataResourceInfo
                    .withRepositoryAccess(classInfo, idClassName, entityClassName, repositoryClassName);
            resourcesProducer.produce(new RestDataResourceBuildItem(resourceInfo));
            unremovableBeansProducer.produce(
                    new UnremovableBeanBuildItem(new UnremovableBeanBuildItem.BeanClassNameExclusion(repositoryClassName)));
        }
    }

    private void validateResource(IndexView index, ClassInfo classInfo) {
        if (!Modifier.isInterface(classInfo.flags())) {
            throw new RuntimeException(classInfo.name() + " has to be an interface");
        }

        if (classInfo.interfaceNames().size() > 1) {
            throw new RuntimeException(classInfo.name() + " should only extend REST Data Panache interface");
        }

        if (!index.getKnownDirectImplementors(classInfo.name()).isEmpty()) {
            throw new RuntimeException(classInfo.name() + " should not be extended or implemented");
        }
    }

    private List<Type> getGenericTypes(ClassInfo classInfo) {
        return classInfo.interfaceTypes()
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException(classInfo.toString() + " does not have generic types"))
                .asParameterizedType()
                .arguments();
    }
}
