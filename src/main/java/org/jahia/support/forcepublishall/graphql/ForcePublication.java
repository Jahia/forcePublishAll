package org.jahia.support.forcepublishall.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.api.Constants;
import org.jahia.exceptions.JahiaRuntimeException;
import org.jahia.modules.graphql.provider.dxm.node.GqlJcrNode;
import org.jahia.modules.graphql.provider.dxm.node.GqlJcrNodeMutation;
import org.jahia.modules.graphql.provider.dxm.node.GqlJcrWrongInputException;
import org.jahia.modules.graphql.provider.dxm.node.NodeQueryExtensions;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.*;
import org.jahia.services.scheduler.BackgroundJob;
import org.jahia.services.scheduler.SchedulerService;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.slf4j.Logger;

import javax.jcr.RepositoryException;
import java.util.*;

/**
 * Admin mutation class for Augmented Search
 */
@GraphQLTypeExtension(GqlJcrNodeMutation.class)
public final class ForcePublication {
    private GqlJcrNodeMutation nodeMutation;
    private transient Logger logger = org.slf4j.LoggerFactory.getLogger(ForcePublication.class);

    protected void validateNodeWorkspace(GqlJcrNode node) {
        JCRSessionWrapper session;
        try {
            session = node.getNode().getSession();
        } catch (RepositoryException e) {
            throw new JahiaRuntimeException(e);
        }
        if (!session.getWorkspace().getName().equals(Constants.EDIT_WORKSPACE)) {
            throw new GqlJcrWrongInputException("Publication fields can only be used with nodes from " + NodeQueryExtensions.Workspace.EDIT + " workspace");
        }
    }

    /**
     * Create a publication mutation extension instance.
     *
     * @param nodeMutation JCR node mutation to apply the extension to
     * @throws GqlJcrWrongInputException In case the parameter represents a node from LIVE rather than EDIT workspace
     */
    public ForcePublication(GqlJcrNodeMutation nodeMutation) throws GqlJcrWrongInputException {
        validateNodeWorkspace(nodeMutation.getNode());
        this.nodeMutation = nodeMutation;
    }

    /**
     * Root search mutation for indexing of the sites
     *
     * @return admin mutation result object
     */
    @GraphQLField
    @GraphQLName("forcePublish")
    @GraphQLDescription("Force the publication of the whole sub-tree by first deleting everything in live and then republishing the whole sub-tree")
    public Boolean forcePublish() throws RepositoryException {
        ComplexPublicationService complexPublicationService = BundleUtils.getOsgiService(ComplexPublicationService.class, null);
        SchedulerService schedulerService = BundleUtils.getOsgiService(SchedulerService.class, null);
        String uuid;
        String path;
        JCRSessionWrapper session;
        Set<String> activeLiveLanguagesSet;
        try {
            JCRNodeWrapper nodeToPublish = nodeMutation.getNode().getNode();
            uuid = nodeToPublish.getIdentifier();
            path = nodeToPublish.getPath();
            activeLiveLanguagesSet = nodeToPublish.getResolveSite().getActiveLiveLanguages();
            session = JCRSessionFactory.getInstance().getCurrentUserSession();
        } catch (RepositoryException e) {
            throw new JahiaRuntimeException(e);
        }
        logger.info("Force publication of node with UUID: {}, path {}", uuid, path);
        JobDetail jobDetail = BackgroundJob.createJahiaJob("Publication", PublicationJob.class);
        JobDataMap jobDataMap = jobDetail.getJobDataMap();
        JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.LIVE_WORKSPACE, null, sessionWrapper -> {
            JCRNodeWrapper node = sessionWrapper.getNodeByUUID(uuid);
            if (node == null) {
                throw new JahiaRuntimeException("Node not found");
            }
            node.remove();
            sessionWrapper.save();
            logger.info("Deleted node with UUID: {}, path {} in live workspace", uuid, path);
            return null;
        });
        Collection<ComplexPublicationService.FullPublicationInfo> fullPublicationInfos = complexPublicationService.getFullPublicationInfos(Collections.singletonList(uuid), activeLiveLanguagesSet, true, session);
        List<String> allUuids = getAllUuids(fullPublicationInfos);
        jobDataMap.put(PublicationJob.PUBLICATION_UUIDS, allUuids);
        jobDataMap.put(PublicationJob.PUBLICATION_PATHS, Collections.singletonList(path));
        jobDataMap.put(PublicationJob.SOURCE, Constants.EDIT_WORKSPACE);
        jobDataMap.put(PublicationJob.DESTINATION, Constants.LIVE_WORKSPACE);
        jobDataMap.put(PublicationJob.CHECK_PERMISSIONS, true);
        try {
            logger.info("Scheduling publication job for node with UUID: {}, path {}, will publish {} nodes in {} languages", uuid, path, allUuids.size(), activeLiveLanguagesSet.size());
            schedulerService.scheduleJobNow(jobDetail);
        } catch (SchedulerException e) {
            throw new JahiaRuntimeException(e);
        }
        return true;
    }

    private static List<String> getAllUuids(Collection<ComplexPublicationService.FullPublicationInfo> fullPublicationInfo) {
        List<String> l = new ArrayList<String>();
        for (ComplexPublicationService.FullPublicationInfo info : fullPublicationInfo) {
            if (info.getPublicationStatus() != PublicationInfo.DELETED) {
                if (info.getNodeIdentifier() != null) {
                    l.add(info.getNodeIdentifier());
                }
                if (info.getTranslationNodeIdentifier() != null) {
                    l.add(info.getTranslationNodeIdentifier());
                }
                if (info.getDeletedTranslationNodeIdentifiers() != null) {
                    l.addAll(info.getDeletedTranslationNodeIdentifiers());
                }
            }
        }
        return l;
    }
}
