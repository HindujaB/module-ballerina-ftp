/*
 * Copyright (c) 2018 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.compiler;

import org.ballerinalang.compiler.plugins.AbstractCompilerPlugin;
import org.ballerinalang.compiler.plugins.SupportEndpointTypes;
import org.ballerinalang.connector.api.BallerinaConnectorException;
import org.ballerinalang.ftp.util.FTPUtil;
import org.ballerinalang.ftp.util.ServerConstants;
import org.ballerinalang.model.tree.AnnotationAttachmentNode;
import org.ballerinalang.model.tree.EndpointNode;
import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.ServiceNode;
import org.ballerinalang.model.tree.expressions.ExpressionNode;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.util.diagnostic.DiagnosticLog;
import org.ballerinalang.util.exceptions.BallerinaException;
import org.wso2.ballerinalang.compiler.semantics.model.types.BStructType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.tree.BLangResource;
import org.wso2.ballerinalang.compiler.tree.BLangVariable;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef;

import java.util.List;

import static org.ballerinalang.ftp.util.ServerConstants.FTP_SERVER_EVENT;

/**
 * Compiler plugin for validating FTP Listener.
 */
@SupportEndpointTypes(value = {
        @SupportEndpointTypes.EndpointType(orgName = "wso2",
                                           packageName = "ftp",
                                           name = "Listener")
        }
)
public class FTPMonitorServiceCompilerPlugin extends AbstractCompilerPlugin {
    @Override
    public void init(DiagnosticLog diagnosticLog) {
    }

    @Override
    public void process(ServiceNode serviceNode, List<AnnotationAttachmentNode> annotations) {
        List<BLangResource> resources = (List<BLangResource>) serviceNode.getResources();
        if (resources == null || resources.size() == 0) {
            throw new BallerinaConnectorException(
                    "No resources define for given service: " + serviceNode.getName().getValue());
        } else if (resources.size() >= 2) {
            throw new BallerinaConnectorException(
                    "More than one resource define for given service: " + serviceNode.getName().getValue());
        }
        final List<BLangVariable> parameters = resources.get(0).getParameters();
        if (parameters.size() != 1) {
            throw new BallerinaConnectorException("Invalid resource signature. Only a single " + FTP_SERVER_EVENT
                    + " parameter allow in the resource signature.");
        }
        final BType type = parameters.get(0).getTypeNode().type;
        if (type.getKind().equals(TypeKind.STRUCT)) {
            if (type instanceof BStructType) {
                BStructType event = (BStructType) type;
                if (!"ftp".equals(event.tsymbol.pkgID.name.value)
                        || !FTP_SERVER_EVENT.equals(event.tsymbol.name.value)) {
                    throw new BallerinaConnectorException("Parameter should be of type - ftp:" + FTP_SERVER_EVENT);
                }
            }
        }
    }

    @Override
    public void process(EndpointNode endpointNode, List<AnnotationAttachmentNode> annotations) {
        final ExpressionNode configurationExpression = endpointNode.getConfigurationExpression();
        if (NodeKind.RECORD_LITERAL_EXPR.equals(configurationExpression.getKind())) {
            BLangRecordLiteral recordLiteral = (BLangRecordLiteral) configurationExpression;
            boolean isNonEmptyPath = false;
            boolean isNonEmptyHost = false;
            boolean isValidProtocol = false;
            for (BLangRecordLiteral.BLangRecordKeyValue config : recordLiteral.getKeyValuePairs()) {
                final String key = ((BLangSimpleVarRef) config.getKey()).variableName.value;
                final Object value = ((BLangLiteral) config.getValue()).getValue();
                switch (key) {
                    case ServerConstants.ANNOTATION_PATH:
                        if (value != null) {
                            if (!value.toString().isEmpty()) {
                                isNonEmptyPath = true;
                            }
                        }
                        break;
                    case ServerConstants.ANNOTATION_HOST:
                        if (value != null) {
                            if (!value.toString().isEmpty()) {
                                isNonEmptyHost = true;
                            }
                        }
                        break;
                    case ServerConstants.ANNOTATION_PROTOCOL:
                        if (value != null) {
                            if (!value.toString().isEmpty()) {
                                isValidProtocol = !FTPUtil.notValidProtocol(value.toString());
                            }
                        }
                        break;
                    default:
                        // Do nothing.
                }
            }
            if (!isNonEmptyPath) {
                throw new BallerinaException("Cannot create FTP Listener without path.");
            }
            if (!isNonEmptyHost) {
                throw new BallerinaException("Cannot create FTP Listener without host.");
            }
            if (!isValidProtocol) {
                throw new BallerinaException("Only FTP, SFTP and FTPS protocols are supported by FTP listener.");
            }
        }
    }
}
